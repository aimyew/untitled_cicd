import request from './request'

export const serverApi = {
  list: (params = {}) => request.get('/servers', { params }),
  save: (data) => request.post('/servers', data),
  remove: (id) => request.delete(`/servers/${id}`),
  test: (id) => request.post(`/servers/${id}/test`)
}

export const projectApi = {
  list: (params = {}) => request.get('/projects', { params }),
  save: (data) => request.post('/projects', data),
  remove: (id) => request.delete(`/projects/${id}`)
}

export const deployApi = {
  start: (projectId) => request.post(`/deploy/${projectId}`),
  records: (params) => request.get('/deploy/records', { params }),
  recordDetail: (id) => request.get(`/deploy/records/${id}`),
  lastByProjects: (projectIds) => request.get('/deploy/last', { params: { projectIds } })
}
